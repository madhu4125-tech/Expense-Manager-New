My Expenses - native Kotlin (Jetpack Compose) version

Build: push to GitHub with .github/workflows/build-apk.yml, then download the
"expense-manager-apk" artifact from the Actions run.

Data migration: in the OLD web app use Backup > Export backup (JSON), then in
this app use Backup > Import backup (JSON).

SMS import: header SMS icon (or Backup & import > Import from SMS). Needs the
READ_SMS permission. On Android 13+ for sideloaded apps: App info > menu >
Allow restricted settings, then Permissions > SMS > Allow.

Auto-add: SMS icon > "Auto-add new bank SMS". Needs RECEIVE_SMS (and notification
permission on Android 13+). Each captured alert shows a notification with Undo.
