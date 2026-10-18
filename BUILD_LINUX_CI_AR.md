# بناء WolFox على Linux / GitHub Actions

هذه الحزمة تحتفظ ببنية Xposed الأصلية ولا تثبت التوافق مع Android 16 أو LSPatch.

## محليًا
ثبّت JDK 17 وAndroid SDK platform 34 وbuild-tools 34.0.0. ثم:

```bash
bash tests-linux/check.sh
bash gradlew --no-daemon --stacktrace :app:testDebugUnitTest :app:assembleDebug
```

المخرج المتوقع عند النجاح: `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub
ارفع **كامل محتويات هذا المجلد** إلى فرع منفصل اسمه `wolfox-current-structure-build` في المستودع، لا ترفع ملف workflow وحده. ثم افتح Actions > WolFox current-structure APK build > Run workflow.

سيفشل سير العمل عمدًا إذا كان السورس مختلفًا أو غاب `FloatingManager` / `GPSMockManager` أو لم يولد APK. يحفظ APK في Artifacts بعد نجاح الاختبارات والبناء والتوقيع debug.

لا تعادل نتيجة testDebugUnitTest نجاحًا على جهاز Android 16، ولا تعادل debug APK نسخة release موقعة بمفتاح إنتاج.
