const { v2: cloudinary } = require('cloudinary')

function isConfigured() {
  return Boolean(process.env.CLOUDINARY_URL)
}

function uploadPaymentScreenshot(buffer, originalName) {
  if (!isConfigured()) {
    return Promise.reject(new Error('CLOUDINARY_URL is not set'))
  }

  const safe = String(originalName || 'receipt')
    .replace(/[^a-zA-Z0-9.\-_]/g, '_')
    .slice(0, 80)

  return new Promise((resolve, reject) => {
    const stream = cloudinary.uploader.upload_stream(
      {
        folder: 'slam/payments',
        resource_type: 'image',
        public_id: `${Date.now()}-${safe}`.replace(/\.[^.]+$/, ''),
      },
      (err, result) => {
        if (err || !result) {
          reject(err || new Error('Cloudinary upload failed'))
          return
        }
        resolve(result.secure_url)
      },
    )
    stream.end(buffer)
  })
}

module.exports = { isConfigured, uploadPaymentScreenshot }
