const { describe, it } = require('node:test')
const assert = require('node:assert/strict')
const { expiryAction } = require('../utils/subscription')

const DAY = 24 * 60 * 60 * 1000
const free = { price_pkr: 0 }
const basic = { price_pkr: 299 }
const premium = { price_pkr: 699, monthly_limit: null }

function sub(overrides = {}) {
  return { status: 'active', end_date: '2026-10-13', ...overrides }
}

describe('expiryAction', () => {
  const endOfPeriodDay = Date.parse('2026-10-13T00:00:00Z')

  it('does nothing before the end date', () => {
    assert.equal(expiryAction(sub(), basic, endOfPeriodDay - DAY), 'none')
  })

  it('treats the end date as a full day (plan lasts through it)', () => {
    assert.equal(expiryAction(sub(), basic, endOfPeriodDay + DAY - 1), 'none')
    assert.equal(expiryAction(sub(), basic, endOfPeriodDay + DAY), 'expire_to_free')
  })

  it('drops an expired paid plan to Free, whatever the plan or cancel flag', () => {
    const after = endOfPeriodDay + 2 * DAY
    assert.equal(expiryAction(sub(), basic, after), 'expire_to_free')
    assert.equal(expiryAction(sub(), premium, after), 'expire_to_free')
    assert.equal(expiryAction(sub({ cancel_at_period_end: true }), basic, after), 'expire_to_free')
  })

  it('starts the next period for an expired Free plan', () => {
    assert.equal(expiryAction(sub(), free, endOfPeriodDay + 2 * DAY), 'renew_free')
  })

  it('ignores subscriptions that are not active or have no end date', () => {
    const after = endOfPeriodDay + 5 * DAY
    assert.equal(expiryAction(sub({ status: 'pending_payment' }), basic, after), 'none')
    assert.equal(expiryAction(sub({ status: 'paused' }), basic, after), 'none')
    assert.equal(expiryAction(sub({ end_date: null }), basic, after), 'none')
    assert.equal(expiryAction(null, basic, after), 'none')
  })
})
