# SMSManager — Android 16

تطبيق عربي RTL لإدارة وإرسال رسائل SMS باستخدام شريحة SIM في الهاتف.

## البناء السحابي
تمت إضافة GitHub Actions في:
`.github/workflows/build-apk.yml`

بعد رفع المشروع إلى GitHub:
1. افتح **Actions**.
2. اختر **Build Android APK**.
3. اضغط **Run workflow**.
4. انتظر حتى تظهر علامة النجاح.
5. افتح نتيجة التشغيل.
6. من **Artifacts** حمّل `SMSManager-debug-APK`.
7. فك الضغط وستجد `app-debug.apk`.

## مهم
هذه النسخة هي نسخة أولية قابلة للبناء والاختبار. الإرسال يستخدم Android SMS API، بينما وظائف النسخة النهائية مثل CSV، قاعدة بيانات Room، اختيار SIM الفعلي، وتقارير حالة الإرسال/التسليم تحتاج استكمالًا.
