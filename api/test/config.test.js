const { describe, it } = require('node:test')
const assert = require('node:assert/strict')
const {
  publicFields,
  normalizeMerchant,
  DEFAULT_MERCHANT,
} = require('../utils/config')

describe('publicFields', () => {
  it('prefers DB merchant numbers over defaults', () => {
    const out = publicFields({
      sms_prefix: 'SLAM',
      pin_min_length: 4,
      pin_max_length: 6,
      pin_attempt_cap: 3,
      pin_window_minutes: 15,
      login_attempt_cap: 3,
      login_window_minutes: 15,
      maintenance: false,
      payments_enabled: true,
      maps_enabled: false,
      email_enabled: true,
      emergency_enabled: true,
      emergency_interval_hours: 1,
      easypaisa_account: '03001234567',
      jazzcash_account: '03007654321',
    })
    assert.equal(out.easypaisa_account, '03001234567')
    assert.equal(out.jazzcash_account, '03007654321')
  })

  it('falls back when merchant blank or invalid', () => {
    const out = publicFields({
      easypaisa_account: '  ',
      jazzcash_account: '12',
    })
    assert.equal(out.easypaisa_account, process.env.EASYPAY_ACCOUNT || process.env.PAYMENT_ACCOUNT || DEFAULT_MERCHANT)
    assert.equal(out.jazzcash_account, process.env.JAZZCASH_ACCOUNT || process.env.PAYMENT_ACCOUNT || DEFAULT_MERCHANT)
  })

  it('ensures pin max is at least pin min', () => {
    const out = publicFields({
      pin_min_length: 6,
      pin_max_length: 4,
    })
    assert.equal(out.pin_min_length, 6)
    assert.equal(out.pin_max_length, 6)
  })
})

describe('normalizeMerchant', () => {
  it('strips non-digits', () => {
    assert.equal(normalizeMerchant('0300-1234567', DEFAULT_MERCHANT), '03001234567')
  })
})
