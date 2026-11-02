# R8 rules for release builds. Most libraries here (kotlinx.serialization, Room, WorkManager,
# Tink, OkHttp) ship their own; these cover what they don't.

# Credential Manager finds its Play services provider by reflection (Android's documented rule).
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}
