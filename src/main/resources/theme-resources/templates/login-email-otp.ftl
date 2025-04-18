<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('email-otp'); section>
<!-- template: login-email-otp.ftl -->
    <#if section = "header">
        ${msg("doLogIn")}
    <#elseif section = "form">
        <form id="kc-otp-login-form" class="${properties.kcFormClass!}" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">
            <div class="${properties.kcFormGroupClass!}">
                <div class="${properties.kcLabelWrapperClass!}">
                    <label for="email-otp" class="${properties.kcLabelClass!}">${msg("loginEmailOtp")}</label>
                </div>

                <div class="${properties.kcInputWrapperClass!}">
                    <input id="email-otp" name="email-otp" autocomplete="one-time-code" type="text" class="${properties.kcInputClass!}" autofocus=true aria-invalid="<#if messagesPerField.existsError('email-otp')>true</#if>" dir="ltr" />

                    <#if messagesPerField.existsError('email-otp')>
                        <span id="input-error-email-otp-code" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                            ${kcSanitize(messagesPerField.get('email-otp'))?no_esc}
                        </span>
                    </#if>
                </div>
            </div>

            <div class="${properties.kcFormGroupClass!}">
                <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                    <div class="${properties.kcFormButtonsWrapperClass!}">
                        <button
                            class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                            name="login"
                            id="kc-login"
                            type="submit"
                        >${kcSanitize(msg("doLogIn"))?no_esc}</button>

                        <button
                            class="${properties.kcButtonClass!} ${properties.kcButtonSecondaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                            name="resend-email"
                            id="kc-resend-email"
                            type="submit"
                        >${kcSanitize(msg("doResendEmail"))?no_esc}</button>
                    </div>
                </div>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
