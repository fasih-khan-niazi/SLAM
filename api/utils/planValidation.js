/** Validation for admin-edited plan fields. Returns human-readable problems; empty means OK. */

function isWholeNumber(value, min, max) {
  if (value === '' || value === null || value === undefined || typeof value === 'boolean') return false
  const n = Number(value)
  return Number.isInteger(n) && n >= min && n <= max
}

function isUnlimited(value) {
  return value === null || value === '' || value === 'null'
}

/**
 * @param {object} body request body
 * @param {{requireName?: boolean}} options requireName for creating a plan
 */
function validatePlanBody(body, { requireName = false } = {}) {
  const errors = []
  if (requireName || body.name !== undefined) {
    const name = String(body.name ?? '').trim()
    if (!name) errors.push('Plan name is required')
    else if (name.length > 64) errors.push('Plan name must be 64 characters or fewer')
  }
  if (body.price_pkr !== undefined && body.price_pkr !== null && !isWholeNumber(body.price_pkr, 0, 1_000_000)) {
    errors.push('price_pkr must be a whole number from 0 to 1000000')
  }
  // null (or empty) means unlimited. A negative limit would read as "unlimited" on phones.
  if (body.monthly_limit !== undefined && !isUnlimited(body.monthly_limit) &&
      !isWholeNumber(body.monthly_limit, 0, 1_000_000)) {
    errors.push('monthly_limit must be empty (unlimited) or a whole number from 0 to 1000000')
  }
  if (body.max_contacts !== undefined && body.max_contacts !== null &&
      !isWholeNumber(body.max_contacts, 1, 100)) {
    errors.push('max_contacts must be a whole number from 1 to 100')
  }
  return errors
}

module.exports = { validatePlanBody }
