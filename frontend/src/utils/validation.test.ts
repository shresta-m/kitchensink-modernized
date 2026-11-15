import { describe, expect, it } from 'vitest'
import { hasErrors, validateAdminSignup, validateCredentials, validateMember } from './validation'

const ok = { name: 'John Smith', email: 'john@example.com', phoneNumber: '2125551212' }

describe('validateMember', () => {
  it('accepts a valid member', () => {
    expect(hasErrors(validateMember(ok))).toBe(false)
  })
  it('rejects digits and long names', () => {
    expect(validateMember({ ...ok, name: 'R2D2' }).name).toBeDefined()
    expect(validateMember({ ...ok, name: 'x'.repeat(26) }).name).toBeDefined()
    expect(validateMember({ ...ok, name: 'x'.repeat(25) }).name).toBeUndefined()
  })
  it('requires a dot in the email domain', () => {
    expect(validateMember({ ...ok, email: 'a@b' }).email).toBeDefined()
    expect(validateMember({ ...ok, email: 'a@b.co' }).email).toBeUndefined()
  })
  it('requires 10 to 12 digit phones', () => {
    expect(validateMember({ ...ok, phoneNumber: '123456789' }).phoneNumber).toBeDefined()
    expect(validateMember({ ...ok, phoneNumber: '1234567890123' }).phoneNumber).toBeDefined()
    expect(validateMember({ ...ok, phoneNumber: '21255512ab' }).phoneNumber).toBeDefined()
    expect(validateMember({ ...ok, phoneNumber: '212555121212' }).phoneNumber).toBeUndefined()
  })
})

describe('validateCredentials', () => {
  it('accepts a valid username and password', () => {
    expect(validateCredentials('jane.doe', 'a-long-password-1', 'a-long-password-1')).toEqual({})
  })
  it('rejects bad usernames', () => {
    expect(validateCredentials('ab', 'a-long-password-1').username).toBeDefined()
    expect(validateCredentials('has space', 'a-long-password-1').username).toBeDefined()
    expect(validateCredentials('x'.repeat(51), 'a-long-password-1').username).toBeDefined()
    expect(validateCredentials('Jane_Doe-1', 'a-long-password-1').username).toBeUndefined()
  })
  it('requires 12 to 128 character passwords', () => {
    expect(validateCredentials('jane', 'short').password).toBeDefined()
    expect(validateCredentials('jane', 'x'.repeat(129)).password).toBeDefined()
    expect(validateCredentials('jane', 'x'.repeat(12)).password).toBeUndefined()
  })
  it('requires the confirmation to match', () => {
    expect(validateCredentials('jane', 'a-long-password-1', 'different-password').confirm).toBeDefined()
  })
})

describe('validateAdminSignup', () => {
  it('requires the admin code on top of the credential rules', () => {
    expect(validateAdminSignup('boss.one', 'a-long-password-1', 'a-long-password-1', '  ').adminCode).toBeDefined()
    expect(validateAdminSignup('boss.one', 'a-long-password-1', 'a-long-password-1', 'code')).toEqual({})
    expect(validateAdminSignup('b', 'short', 'x', 'code').username).toBeDefined()
  })
})
