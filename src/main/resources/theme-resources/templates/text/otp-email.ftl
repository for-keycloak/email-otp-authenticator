<#ftl output_format="plainText">
${kcSanitize(msg("emailOtpYourAccessCode"))}

${otp}

${kcSanitize(msg("emailOtpExpiration", (ttl / 60)?int))}
