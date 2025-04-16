<#import "template.ftl" as layout>
<@layout.emailLayout>
<p>${kcSanitize(msg("emailOtpYourAccessCode"))?no_esc}</p>
<h1>${otp?no_esc}</h1>
<p>${kcSanitize(msg("emailOtpExpiration", (ttl / 60)?int))?no_esc}</p>
</@layout.emailLayout>
