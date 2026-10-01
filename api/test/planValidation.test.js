const { describe, it } = require('node:test')
const assert = require('node:assert/strict')
const { validatePlanBody } = require('../utils/planValidation')

describe('validatePlanBody', () => {
  it('accepts a normal plan, with unlimited as empty or null', () => {
    assert.deepEqual(validatePlanBody({ name: 'Basic', price_pkr: 299, monthly_limit: 50, max_contacts: 5 }, { requireName: true }), [])
    assert.deepEqual(validatePlanBody({ monthly_limit: null }), [])
    assert.deepEqual(validatePlanBody({ monthly_limit: '' }), [])
    assert.deepEqual(validatePlanBody({ monthly_limit: 'null' }), [])
  })

  it('requires a name when creating', () => {
    assert.equal(validatePlanBody({ price_pkr: 10 }, { requireName: true }).length, 1)
    assert.equal(validatePlanBody({ name: '   ' }, { requireName: true }).length, 1)
  })

  it('rejects negative or fractional limits, prices and contact counts', () => {
    assert.equal(validatePlanBody({ monthly_limit: -1 }).length, 1)
    assert.equal(validatePlanBody({ monthly_limit: 2.5 }).length, 1)
    assert.equal(validatePlanBody({ price_pkr: -5 }).length, 1)
    assert.equal(validatePlanBody({ max_contacts: 0 }).length, 1)
    assert.equal(validatePlanBody({ max_contacts: 101 }).length, 1)
  })

  it('ignores fields that are not sent on a partial update', () => {
    assert.deepEqual(validatePlanBody({ description: 'x' }), [])
  })
})
