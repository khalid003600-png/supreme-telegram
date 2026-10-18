# مراجع تبعيات البناء

مكتبة Xposed API يجب أن تُستخدم للتجميع فقط ولا تُدمج فئاتها داخل APK؛ إطار التشغيل يوفّر التنفيذ الحقيقي. تعليمات المشروع الرسمية القديمة تستخدم `provided`، والمقابل في Gradle الحديث هو `compileOnly`. مستودع jCenter المذكور في الصفحة قديم؛ تم التحقق من الوصول إلى ملف API 82 على النطاق الرسمي `api.xposed.info`.

- https://github.com/rovo89/xposedbridge/wiki/using-the-xposed-framework-api
- https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar
- https://developer.android.com/build/remote-repositories

في بيئة البناء الحالية يتوفر Java 17 وGradle 8.9 وSDK 35 وBuild Tools 35.0.0. هذه نتيجة فحص البيئة الحالية، وليست ادعاءً بأن الموديول اختُبر على هاتف أو بأن كل إطار Xposed متوافق مع Android 16.
