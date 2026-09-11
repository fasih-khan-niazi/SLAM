const test = require('node:test')
const assert = require('node:assert/strict')
const { normalizePhone, validateLogin, validateRegister } = require('../utils/validation')

test('login requires a syntactically valid email', () => {
  assert.equal(validateLogin({ email: 'not-an-email', password: 'password123' }), 'Enter a valid email address')
  assert.equal(validateLogin({ email: 'owner@example.com', password: 'password123' }), null)
})

test('registration validates and normalizes international phone numbers', () => {
  assert.equal(normalizePhone('+92 300-1234567'), '+923001234567')
  assert.equal(validateRegister({
    name: 'Owner',
    email: 'owner@example.com',
    password: 'password123',
    phone: '+92 300-1234567',
  }), null)
})
