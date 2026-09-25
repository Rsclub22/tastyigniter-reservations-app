# kotlinx.serialization is used only with JsonElement trees (no generated serializers),
# so no extra keep rules are needed. OkHttp ships its own consumer rules.
-dontwarn org.slf4j.**
