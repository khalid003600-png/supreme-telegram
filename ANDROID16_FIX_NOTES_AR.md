# إصلاحات موضعية — بدون تغيير بنية WolFox

- FloatingManager: حدود سحب الزر وحفظ موضعه تراعي أشرطة النظام والـ cutout عند API 30+، مع تجنب إضافة الإزاحة مرتين عندما تكون الحاوية تحت شريط الحالة.
- GPSMockManager: اختيار آخر موقع مخزن بحسب الزمن أولاً، والدقة فقط عند تساوي الزمن؛ رفض الإحداثيات غير الصالحة؛ حماية buildLocation من null.
- لم تُغيّر ملفات Xposed أو package ID أو إعدادات SDK أو آلية الحقن. **هذا لا يثبت عمل LSPatch على Android 16**.

## Linux
`bash tests-linux/check.sh`

اختبار بناء فعلي يحتاج Android SDK والاعتماديات المخزنة (أو شبكة لتنزيلها): `./gradlew :app:assembleDebug`.

## Android 16 فقط (محاكي Linux أو جهاز adb)
`adb shell getprop ro.build.version.sdk` يجب أن يعطي 36.
`adb logcat -c; adb shell am start -n <PACKAGE>/<ACTIVITY>; adb logcat -d -v time > wolfox.log`
اختبر إظهار الزر، سحبه لأطراف الشاشة، تدوير الشاشة، إيقاف/استئناف Activity، ورفض إذن الموقع. تحقق من عدم وجود `FATAL EXCEPTION` أو `SecurityException`.

**النجاح**: بناء ناجح وسجل تشغيل خالٍ من الانهيار والزر يبقى داخل الشاشة بعد التدوير. **الفشل**: أي انهيار أو زر خارج الحدود أو عدم تجميع. الاختبار الساكن وحده لا يثبت النجاح.
