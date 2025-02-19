plugins {
    java
    application
}

group = "com.noasaba.discordpoll"
version = "1.0"

application {
    mainClass.set("com.noasaba.discordpoll.Main")
}

repositories {
    mavenCentral()
    maven { url = uri("https://m2.dv8tion.net/releases") }
}

dependencies {
    // Discord Bot 用 JDA（起動通知などに利用）
    implementation("net.dv8tion:JDA:4.3.0_277")
    // HTTP リクエスト用 OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    // JSON 操作用 org.json
    implementation("org.json:json:20210307")
    // YAML 読み込み用 SnakeYAML
    implementation("org.yaml:snakeyaml:1.30")
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes["Main-Class"] = application.mainClass.get() }
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) } })
}
