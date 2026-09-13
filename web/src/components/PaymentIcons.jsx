/** Brand marks for JazzCash / EasyPaisa payment UI (local assets). */

export function JazzCashIcon({ size = 36 }) {
  return (
    <img
      className="method-icon"
      src="/payment/jazzcash.png"
      alt=""
      width={size}
      height={size}
      style={{ width: size, height: size, objectFit: 'contain', borderRadius: 8 }}
    />
  )
}

export function EasyPaisaIcon({ size = 36 }) {
  return (
    <img
      className="method-icon"
      src="/payment/easypaisa.jpg"
      alt=""
      width={size}
      height={size}
      style={{ width: size, height: size, objectFit: 'contain', borderRadius: 8 }}
    />
  )
}
