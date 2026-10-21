/**
 * Session rules shared by the auth middleware and the password routes.
 * Tokens carry an issued-at (iat, whole seconds). A password change records the moment it
 * happened, and every token issued before that moment stops working.
 */

/** Current time cut to whole seconds, so database rounding can never push it past a new token. */
function currentSecond() {
  return new Date(Math.floor(Date.now() / 1000) * 1000)
}

function issuedBeforePasswordChange(decoded, user) {
  if (!user || !user.password_changed_at) return false
  const changedSec = Math.floor(new Date(user.password_changed_at).getTime() / 1000)
  if (Number.isNaN(changedSec)) return false
  return Number(decoded && decoded.iat ? decoded.iat : 0) < changedSec
}

module.exports = { currentSecond, issuedBeforePasswordChange }
