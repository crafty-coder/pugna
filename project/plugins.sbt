addSbtPlugin("com.lucidchart"    % "sbt-scalafmt"        % "1.14")
addSbtPlugin("com.thesamet"      % "sbt-protoc"          % "0.99.13")
addSbtPlugin("com.typesafe.sbt"  % "sbt-native-packager" % "1.3.2")
addSbtPlugin("de.heikoseeberger" % "sbt-header"          % "4.0.0")
addSbtPlugin("io.spray"          % "sbt-revolver"        % "0.9.1")

libraryDependencies ++= Seq(
  "com.trueaccord.scalapb" %% "compilerplugin" % "0.6.7"
)
