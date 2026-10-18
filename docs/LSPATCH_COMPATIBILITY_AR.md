# توافق ملف LSPatch المرفق مع الموديول

المدخل `lspatch.zip` حجمه 85147454 بايت، وبصمته SHA-256 `25567ea237e01630c4552977dfe4d3e368850065f5931ebf07db85224a645df5`. يحتوي 15 مدخلًا تم جردها دون تشغيل أي APK أو مكتبة. الملف مجموعة أصول LSPatch مع APK أصلي وموديول، وليس manager.apk أو lspatch.jar أو APK مُثبّت مستقلًا.

## الإعدادات المقروءة

ملف `lspatch/config.json` يحدد LSPatch versionName `1.2`، versionCode `487`، API_CODE `102`، coreVersion `canary-3106` / `7306`، وcore hash `e00c5c5038bbcc14e0b382ab893301d6993e6989`.

الوضع `useManager=false`، و`injectDex=false`، و`debuggable=false`، و`sigBypassLevel=0`، و`addedPermissions=[]`، و`injectDocumentsProvider=false`. لم نعدّل هذه الإعدادات أو نفعّل تجاوز توقيع أو DocumentsProvider. توجد مكتبات أربع معماريات arm64-v8a وarmeabi-v7a وx86 وx86_64، وتم جردها دون تنفيذ.

الموديول الموجود داخل الأرشيف هو نسخة **Embedded السابقة**، ببصمة `c713ec951a78ed0b4c7ad67ce45f0d0f496b25c2a9fcedb690aca4be3d257a95`. لذلك إصلاحات UIAudit وسياسة الظهور الأولى لا تصل إلى هذا الملف تلقائيًا؛ المصدر والموديول الجديدان تسليم مستقل، وليس ادعاء استبداله داخل APK طرف ثالث.

## مسار التحميل

مصدر LSPatch v1.2 يصرّح بأن كلا نوعي Xposed مدعومان: `de.robv.android.xposed` القديم وlibxposed الحديث. الرقم 102 الخاص بالإطار لا يلزم تحويل هذا الموديول إلى libxposed أو وضع اعتماد API حديث داخل APK؛ نقطة `IXposedHookLoadPackage` القديمة والأصل `assets/xposed_init` ما زالا مسارًا مدعومًا. المصدر يحدد Android 9 / API 28 أو أحدث لإطار LSPatch، حتى لو كان minSdk الموديول نفسه 21. [1]

`LocalApplicationService` في v1.2 يقرأ `assets/lspatch/modules` ويستخرج ملفات APK إلى كاش المضيف، ثم يفصل legacy عن modern modules. لذلك ينبغي قراءة أيقونة وHTML والخطوط من أصول **الموديول نفسه عبر classloader** أو عبر أصول صريحة أضيفت إلى مشروع المضيف؛ لا يجوز افتراض أن `context.getAssets()` للمضيف يحتوي أصول APK الموديول تلقائيًا. [2]

تسلسل LSPatch يجهز classloader المضيف ويدعو legacy `handleLoadPackage`؛ بعض مسارات legacy resource hooking قد لا تتاح على جهاز معين دون تعطيل method hooking. لذلك لا يعتمد تحديث الواجهة على XResources أو تعديل ActivityThread الخاص لمجرد تحميل الأصول. [3]

ملاحظات v1.2 تتضمن إصلاحات local-mode/manager والتوقيع، ولا تبرر تغيير `sigBypassLevel=0` في الملف المرفق. [4]

## حدود الفحص

تم فحص هوية Manifest الأصلية فقط بأدوات Android الساكنة: package `app.t2.availo`، versionName `2.6.2`، versionCode `128`، minSdk `24` وtargetSdk `36`. لم يُفك كود APK الأصل ولم يُعدّل أو يُدمج أو يُعاد توقيعه أو يُثبّت. اختبار source host policy يستطيع إثبات عدم قيد اسم الحزمة، لكنه لا يثبت توافق هذا التطبيق بعينه أو السماح بتعديله.

لم تُنفّذ loader.dex أو مكتبات LSPatch أو APK الأصلي أو اتصال تفعيل أو مسار hooks على جهاز. الأدلة تثبت تطابق **عقود التحميل والبيانات**؛ logs جهاز من البيئة المصرح بها لازمة لتأكيد تحميل الموديول وظهور الأيقونة وتشغيل المحرك الفعلي. بقاء علامة Mock والتفعيل والانتهاء وسياسة الإخفاء الجديدة لا تتغير بهدف التوافق.

## References

[1]: https://github.com/JingMatrix/LSPatch "JingMatrix LSPatch support for legacy and modern modules"
[2]: https://raw.githubusercontent.com/JingMatrix/LSPatch/v1.2/patch-loader/src/main/java/org/lsposed/lspatch/service/LocalApplicationService.java "LSPatch v1.2 local embedded module loading"
[3]: https://raw.githubusercontent.com/JingMatrix/LSPatch/v1.2/patch-loader/src/main/java/org/lsposed/lspatch/loader/LSPApplication.java "LSPatch v1.2 application bootstrap and legacy lifecycle"
[4]: https://github.com/JingMatrix/LSPatch/releases/tag/v1.2 "LSPatch v1.2 release notes"
