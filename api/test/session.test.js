const { describe, it } = require('node:test')
const assert = require('node:assert/strict')
const { currentSecond, issuedBeforePasswordChange } = require('../utils/session')
const { deviceIdFrom, loginKeys } = require('../middleware/rateLimit')

describe('issuedBeforePasswordChange', () => {
  const changed = new Date('2026-10-01T10:00:00.000Z')
  const sec = Math.floor(changed.getTime() / 1000)

  it('never rejects when the password was never changed', () => {
    assert.equal(issuedBeforePasswordChange({ iat: 1 }, { password_changed_at: null }), false)
  })

  it('rejects tokens issued before the change', () => {
    assert.equal(issuedBeforePasswordChange({ iat: sec - 60 }, { password_changed_at: changed }), true)
  })

  it('accepts tokens issued at or after the change', () => {
    assert.equal(issuedBeforePasswordChange({ iat: sec }, { password_changed_at: changed }), false)
    assert.equal(issuedBeforePasswordChange({ iat: sec + 5 }, { password_changed_at: changed }), false)
  })

  it('treats a token without iat as old once a change exists', () => {
    assert.equal(issuedBeforePasswordChange({}, { password_changed_at: changed }), true)
  })

  it('records the change on a whole second', () => {
    assert.equal(currentSecond().getMilliseconds(), 0)
  })
})

describe('login lockout keys', () => {
  const req = (header, ip = '10.0.0.1') => ({ ip, get: () => header })

  it('accepts a well-formed device id and ignores junk', () => {
    assert.equal(deviceIdFrom(req('3f2c9a1e-7b44-4d0a-9c11-0a7e5d2b8f60')), '3f2c9a1e-7b44-4d0a-9c11-0a7e5d2b8f60')
    assert.equal(deviceIdFrom(req('short')), '')
    assert.equal(deviceIdFrom(req('has spaces and ; symbols!!!')), '')
    assert.equal(deviceIdFrom(req(undefined)), '')
  })

  it('separates two devices that share an IP', () => {
    const a = loginKeys(req('device-aaaaaaaa'))
    const b = loginKeys(req('device-bbbbbbbb'))
    assert.notEqual(a.device, b.device)
    assert.equal(a.ip, b.ip)
  })

  it('has no device key when the client sends none', () => {
    assert.equal(loginKeys(req(undefined)).device, '')
  })
})
