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
      emergency_interval_minutes: 60,
      easypaisa_account: '03001234567',
      jazzcash_account: '03007654321',
    })
    assert.equal(out.easypaisa_account, '03001234567')
    assert.equal(out.jazzcash_account, '03007654321')
    assert.equal(out.emergency_interval_minutes, 60)
  })

  it('maps legacy emergency hours into minutes', () => {
    const out = publicFields({
      emergency_interval_hours: 2,
    })
    assert.equal(out.emergency_interval_minutes, 120)
  })

  it('falls back when merchant blank or invalid', () => {
    const out = publicFields({
      easypaisa_account: '  ',
      jazzcash_account: '12',
    })
    assert.equal(out.easypaisa_account, process.env.EASYPAY_ACCOUNT || process.env.PAYMENT_ACCOUNT || DEFAULT_MERCHANT)
    assert.equal(out.jazzcash_account, process.env.JAZZCASH_ACCOUNT || process.env.PAYMENT_ACCOUNT || DEFAULT_MERCHANT)
  })

  it('clamps emergency minutes to at least 5', () => {
    const out = publicFields({
      emergency_interval_minutes: 3,
    })
    assert.equal(out.emergency_interval_minutes, 5)
  })
})

describe('normalizeMerchant', () => {
  it('strips non-digits', () => {
    assert.equal(normalizeMerchant('0300-1234567', DEFAULT_MERCHANT), '03001234567')
  })
})

describe('validateConfigPatch', () => {
  const { validateConfigPatch } = require('../utils/config')

  it('accepts PIN attempt caps from 1 to 5', () => {
    for (const n of [1, 3, 5, '2']) {
      assert.deepEqual(validateConfigPatch({ pin_attempt_cap: n }), [])
    }
  })

  it('rejects PIN attempt caps outside 1-5 or non-integers', () => {
    for (const n of [0, 6, 30, -1, 2.5, 'abc', '', true]) {
      assert.equal(validateConfigPatch({ pin_attempt_cap: n }).length, 1, `value ${n}`)
    }
  })

  it('accepts emergency minutes from 5 to 1440', () => {
    for (const n of [5, 60, 1440]) {
      assert.deepEqual(validateConfigPatch({ emergency_interval_minutes: n }), [])
    }
  })

  it('rejects emergency minutes outside 5-1440 or non-integers', () => {
    for (const n of [0, 4, 1441, 7.5, 'soon', '']) {
      assert.equal(validateConfigPatch({ emergency_interval_minutes: n }).length, 1, `value ${n}`)
    }
  })

  it('ignores fields that are not present', () => {
    assert.deepEqual(validateConfigPatch({ maintenance: true }), [])
  })
})

describe('publicFields PIN cap bounds', () => {
  it('clamps a stored cap above 5 down to 5', () => {
    assert.equal(publicFields({ pin_attempt_cap: 30 }).pin_attempt_cap, 5)
  })
  it('keeps a stored cap of 1', () => {
    assert.equal(publicFields({ pin_attempt_cap: 1 }).pin_attempt_cap, 1)
  })
})
