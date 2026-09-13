/**
 * Hardcoded transactional email copy for SLAM.
 * Keep plain text + simple HTML so Gmail and most clients render cleanly.
 */

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function wrapHtml(title, bodyHtml) {
  return `<!DOCTYPE html>
<html>
<head><meta charset="utf-8"><title>${escapeHtml(title)}</title></head>
<body style="margin:0;padding:0;background:#f4f6f8;font-family:Segoe UI,Arial,sans-serif;color:#1a1a1a;">
  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:#f4f6f8;padding:24px 12px;">
    <tr>
      <td align="center">
        <table role="presentation" width="560" cellspacing="0" cellpadding="0" style="max-width:560px;width:100%;background:#ffffff;border-radius:12px;overflow:hidden;border:1px solid #e5e7eb;">
          <tr>
            <td style="padding:20px 24px;background:#0f766e;color:#ffffff;">
              <div style="font-size:20px;font-weight:700;letter-spacing:-0.02em;">SLAM</div>
              <div style="font-size:13px;opacity:0.9;margin-top:4px;">SMS Location Automation for Mobile</div>
            </td>
          </tr>
          <tr>
            <td style="padding:24px;">
              <h1 style="margin:0 0 12px;font-size:18px;line-height:1.35;">${escapeHtml(title)}</h1>
              ${bodyHtml}
            </td>
          </tr>
          <tr>
            <td style="padding:16px 24px;background:#f9fafb;color:#6b7280;font-size:12px;">
              This is an automated message from SLAM. Do not reply to this email.
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>`
}

function paymentSubmittedEmail({
  userName,
  userEmail,
  planName,
  amountPkr,
  paymentMethod,
  transactionId,
  screenshotUrl,
  reviewUrl,
}) {
  const subject = `SLAM — payment to review (${planName})`
  const methodLabel = paymentMethod === 'easypaisa' ? 'EasyPaisa' : 'JazzCash'
  const text =
    `A user submitted a plan upgrade payment that needs review.\n\n` +
    `User: ${userName} (${userEmail})\n` +
    `Plan: ${planName}\n` +
    `Amount: Rs ${amountPkr}\n` +
    `Method: ${methodLabel}\n` +
    `Transaction ID: ${transactionId}\n` +
    `Screenshot: ${screenshotUrl || 'n/a'}\n\n` +
    `Review in admin portal:\n${reviewUrl}\n`

  const bodyHtml = `
    <p style="margin:0 0 16px;line-height:1.5;">A user submitted a plan upgrade payment that needs review.</p>
    <table role="presentation" cellspacing="0" cellpadding="0" style="width:100%;border-collapse:collapse;font-size:14px;">
      <tr><td style="padding:8px 0;color:#6b7280;width:140px;">User</td><td style="padding:8px 0;"><strong>${escapeHtml(userName)}</strong><br><span style="color:#6b7280;">${escapeHtml(userEmail)}</span></td></tr>
      <tr><td style="padding:8px 0;color:#6b7280;">Plan</td><td style="padding:8px 0;">${escapeHtml(planName)}</td></tr>
      <tr><td style="padding:8px 0;color:#6b7280;">Amount</td><td style="padding:8px 0;">Rs ${escapeHtml(amountPkr)}</td></tr>
      <tr><td style="padding:8px 0;color:#6b7280;">Method</td><td style="padding:8px 0;">${escapeHtml(methodLabel)}</td></tr>
      <tr><td style="padding:8px 0;color:#6b7280;">Transaction ID</td><td style="padding:8px 0;"><code>${escapeHtml(transactionId)}</code></td></tr>
    </table>
    <p style="margin:20px 0 0;">
      <a href="${escapeHtml(reviewUrl)}" style="display:inline-block;background:#0f766e;color:#ffffff;text-decoration:none;padding:12px 18px;border-radius:999px;font-weight:600;">Review payment</a>
    </p>
    ${screenshotUrl ? `<p style="margin:16px 0 0;font-size:13px;"><a href="${escapeHtml(screenshotUrl)}">Open screenshot</a></p>` : ''}
  `

  return { subject, text, html: wrapHtml(subject, bodyHtml) }
}

function paymentApprovedEmail({ userName, planName }) {
  const subject = 'Your SLAM subscription is active'
  const text =
    `Hi ${userName},\n\n` +
    `Your payment for ${planName} was approved. Your SLAM plan is now active on this account.\n\n` +
    `Open the Android app to use your updated locate quota and trusted-number limits.\n`
  const bodyHtml = `
    <p style="margin:0 0 12px;line-height:1.5;">Hi ${escapeHtml(userName)},</p>
    <p style="margin:0 0 12px;line-height:1.5;">Your payment for <strong>${escapeHtml(planName)}</strong> was approved. Your SLAM plan is now active.</p>
    <p style="margin:0;line-height:1.5;color:#4b5563;">Open the Android app to use your updated locate quota and trusted-number limits.</p>
  `
  return { subject, text, html: wrapHtml(subject, bodyHtml) }
}

function paymentRejectedEmail({ userName, planName }) {
  const subject = 'SLAM payment could not be verified'
  const text =
    `Hi ${userName},\n\n` +
    `We could not verify your payment for ${planName}. Your current plan is unchanged.\n\n` +
    `You can submit again from the Payments page with a clearer screenshot and the correct transaction ID.\n`
  const bodyHtml = `
    <p style="margin:0 0 12px;line-height:1.5;">Hi ${escapeHtml(userName)},</p>
    <p style="margin:0 0 12px;line-height:1.5;">We could not verify your payment for <strong>${escapeHtml(planName)}</strong>. Your current plan is unchanged.</p>
    <p style="margin:0;line-height:1.5;color:#4b5563;">Submit again from Payments with a clearer screenshot and the correct transaction ID.</p>
  `
  return { subject, text, html: wrapHtml(subject, bodyHtml) }
}

module.exports = {
  paymentSubmittedEmail,
  paymentApprovedEmail,
  paymentRejectedEmail,
}
