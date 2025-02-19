package com.noasaba.discordpoll;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.TextChannel;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.yaml.snakeyaml.Yaml;

import javax.security.auth.login.LoginException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    private static final String DISCORD_API_BASE = "https://discord.com/api/v10";

    public static void main(String[] args) {
        // 1) config.yml が作業ディレクトリに存在しなければ、resources からコピー
        File configFile = new File("config.yml");
        if (!configFile.exists()) {
            try (InputStream defaultConfig = Main.class.getResourceAsStream("/config.yml")) {
                if (defaultConfig == null) {
                    System.err.println("リソース内にデフォルトの config.yml が見つかりません。");
                    return;
                }
                Files.copy(defaultConfig, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("デフォルトの config.yml を生成しました。");
                System.out.println("内容を編集して再度実行してください。");
                return;
            } catch (IOException e) {
                System.err.println("config.yml の生成に失敗しました: " + e.getMessage());
                return;
            }
        }

        // 2) config.yml の読み込み
        Map<String, Object> config;
        try (InputStream in = new FileInputStream(configFile)) {
            Yaml yaml = new Yaml();
            config = yaml.load(in);
        } catch (IOException e) {
            System.err.println("config.yml の読み込みに失敗しました: " + e.getMessage());
            return;
        }

        // 3) Discord 設定の取得
        Map<String, Object> discordConfig = (Map<String, Object>) config.get("discord");
        String token = (String) discordConfig.get("token");
        String channelId = (String) discordConfig.get("channelId");

        // 4) Poll 設定の取得
        Map<String, Object> pollConfig = (Map<String, Object>) config.get("poll");
        String durationStr = (String) pollConfig.get("duration");
        String pollQuestion = (String) pollConfig.get("question");
        String preMessage = (String) pollConfig.get("preMessage");
        List<Map<String, Object>> options = (List<Map<String, Object>>) pollConfig.get("options");

        // 5) プレースホルダー (%month%, %date%) の置換
        LocalDate today = LocalDate.now();
        String month = String.valueOf(today.getMonthValue());
        String date = String.valueOf(today.getDayOfMonth());
        if (pollQuestion != null) {
            pollQuestion = pollQuestion.replace("%month%", month).replace("%date%", date);
        }
        if (preMessage != null) {
            preMessage = preMessage.replace("%month%", month).replace("%date%", date);
        }

        // 6) JDA の初期化
        JDA jda;
        try {
            jda = JDABuilder.createDefault(token).build().awaitReady();
        } catch (LoginException | InterruptedException e) {
            System.err.println("Discord Bot の初期化に失敗しました: " + e.getMessage());
            return;
        }

        // 7) チャンネル取得
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            System.err.println("指定されたチャンネルが見つかりません: " + channelId);
            return;
        }

        // 8) 起動時の preMessage 送信（同期的に送信）
        if (preMessage != null && !preMessage.isEmpty()) {
            channel.sendMessage(preMessage).complete();
        }

        // 9) 投票期間の変換（Poll API は「時間単位」なので最低1時間）
        long durationMillis = parseDurationToMillis(durationStr);
        int durationHours = (int) Math.max(1, Math.ceil(durationMillis / (3600.0 * 1000)));

        // 10) Poll API 用 JSON 作成（絵文字設定あり）
        JSONObject pollObj = new JSONObject();
        JSONObject questionObj = new JSONObject();
        questionObj.put("text", pollQuestion == null ? "投票質問未設定" : pollQuestion);
        pollObj.put("question", questionObj);

        JSONArray answersArray = new JSONArray();
        if (options != null && !options.isEmpty()) {
            for (Map<String, Object> option : options) {
                String content = (String) option.get("content");
                String emojiName = (String) option.get("emojiName");
                String emojiId = (String) option.get("emojiId");
                JSONObject answerObj = new JSONObject();
                JSONObject mediaObj = new JSONObject();
                mediaObj.put("text", content == null ? "" : content);
                // 絵文字設定
                if (emojiId != null && !emojiId.isEmpty()) {
                    JSONObject emojiObj = new JSONObject();
                    emojiObj.put("id", emojiId);
                    mediaObj.put("emoji", emojiObj);
                } else if (emojiName != null && !emojiName.isEmpty()) {
                    JSONObject emojiObj = new JSONObject();
                    emojiObj.put("name", emojiName);
                    mediaObj.put("emoji", emojiObj);
                }
                answerObj.put("poll_media", mediaObj);
                answersArray.put(answerObj);
            }
        }
        pollObj.put("answers", answersArray);
        pollObj.put("duration", durationHours);
        pollObj.put("allow_multiselect", false);
        pollObj.put("layout_type", 1);

        JSONObject jsonBody = new JSONObject();
        jsonBody.put("content", "");
        jsonBody.put("poll", pollObj);

        // 11) Poll 作成 API 呼び出し
        OkHttpClient client = new OkHttpClient();
        String createUrl = DISCORD_API_BASE + "/channels/" + channelId + "/messages";
        RequestBody requestBody = RequestBody.create(jsonBody.toString(), MediaType.get("application/json; charset=utf-8"));
        Request createRequest = new Request.Builder()
                .url(createUrl)
                .post(requestBody)
                .addHeader("Authorization", "Bot " + token)
                .build();

        String messageId;
        try (Response response = client.newCall(createRequest).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                System.err.println("Poll 作成 API 呼び出しに失敗しました: " + response.code() + " " + response.message());
                System.err.println("エラー詳細: " + errorBody);
                return;
            }
            JSONObject respObj = new JSONObject(response.body().string());
            messageId = respObj.getString("id");
            System.out.println("Poll が作成されました。Message ID: " + messageId);
        } catch (IOException e) {
            System.err.println("Poll 作成中にエラーが発生しました: " + e.getMessage());
            return;
        }

        // 12) 投票期間待機
        try {
            System.out.println("Poll 実行中… 持続時間: " + durationMillis + " ミリ秒");
            Thread.sleep(durationMillis);
        } catch (InterruptedException e) {
            System.err.println("待機中に割り込みが発生しました: " + e.getMessage());
        }

        // 13) Poll 終了 API 呼び出し
        String expireUrl = DISCORD_API_BASE + "/channels/" + channelId + "/polls/" + messageId + "/expire";
        Request expireRequest = new Request.Builder()
                .url(expireUrl)
                .post(RequestBody.create(new byte[0]))
                .addHeader("Authorization", "Bot " + token)
                .build();
        try (Response response = client.newCall(expireRequest).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                System.err.println("Poll 終了 API 呼び出しに失敗しました: " + response.code() + " " + response.message());
                System.err.println("エラー詳細: " + errorBody);
            } else {
                System.out.println("Poll が正常に終了しました。Message ID: " + messageId);
            }
        } catch (IOException e) {
            System.err.println("Poll 終了中にエラーが発生しました: " + e.getMessage());
        }

        // 14) 投票結果取得
        String getUrl = DISCORD_API_BASE + "/channels/" + channelId + "/messages/" + messageId;
        Request getRequest = new Request.Builder()
                .url(getUrl)
                .addHeader("Authorization", "Bot " + token)
                .build();
        String winningEndMessage = "";
        try (Response getResponse = client.newCall(getRequest).execute()) {
            if (!getResponse.isSuccessful()) {
                System.err.println("Poll 結果取得に失敗しました: " + getResponse.code() + " " + getResponse.message());
            } else {
                JSONObject msgObj = new JSONObject(getResponse.body().string());
                if (msgObj.has("poll")) {
                    JSONObject pollObjResult = msgObj.getJSONObject("poll");
                    JSONArray answerCounts = null;
                    if (pollObjResult.has("results")) {
                        JSONObject resultsObj = pollObjResult.getJSONObject("results");
                        answerCounts = resultsObj.optJSONArray("answer_counts");
                    }
                    if (answerCounts == null || answerCounts.length() == 0) {
                        System.err.println("投票結果が取得できませんでした。");
                    } else {
                        int maxVotes = -1;
                        int winningConfigIndex = -1;
                        // 注意: answerCounts の順序は config の options とは逆順の場合があるため、逆転させる
                        for (int i = 0; i < answerCounts.length(); i++) {
                            JSONObject answerCount = answerCounts.getJSONObject(i);
                            int count = answerCount.getInt("count");
                            int configIndex = options.size() - 1 - i; // 逆順に対応
                            if (count > maxVotes) {
                                maxVotes = count;
                                winningConfigIndex = configIndex;
                            }
                        }
                        if (winningConfigIndex >= 0 && winningConfigIndex < options.size()) {
                            Map<String, Object> winningOption = options.get(winningConfigIndex);
                            winningEndMessage = (String) winningOption.get("endMessage");
                        }
                    }
                } else {
                    System.err.println("Poll 情報が取得できませんでした。");
                }
            }
        } catch (IOException e) {
            System.err.println("Poll 結果取得中にエラーが発生しました: " + e.getMessage());
        }

        // 15) プレースホルダーを endMessage にも適用し、そのまま送信（前置きテキストは付けない）
        if (!winningEndMessage.isEmpty()) {
            winningEndMessage = winningEndMessage.replace("%month%", month).replace("%date%", date);
            channel.sendMessage(winningEndMessage).complete();
        } else {
            channel.sendMessage("投票結果が取得できませんでした。").complete();
        }

        // 送信完了を待機
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // プログラム終了
        System.out.println("プログラムを終了します。");
        jda.shutdown();
        System.exit(0);
    }

    /**
     * poll.duration の文字列をミリ秒に変換
     * 対応形式: 30s, 1h, 3d, 1w, 2w など（s:秒, h:時間, d:日, w:週）
     */
    private static long parseDurationToMillis(String durationStr) {
        if (durationStr == null || durationStr.isEmpty()) {
            return 3600L * 1000L; // デフォルト1h
        }
        durationStr = durationStr.trim().toLowerCase();
        Pattern pattern = Pattern.compile("(\\d+)([shdw])");
        Matcher matcher = pattern.matcher(durationStr);
        if (matcher.matches()) {
            int value = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);
            switch (unit) {
                case "s": return value * 1000L;
                case "h": return value * 3600L * 1000L;
                case "d": return value * 24L * 3600L * 1000L;
                case "w": return value * 7L * 24L * 3600L * 1000L;
                default: break;
            }
        }
        System.err.println("poll.duration の解析に失敗しました: " + durationStr + ". デフォルトの1hを使用します。");
        return 3600L * 1000L;
    }
}
