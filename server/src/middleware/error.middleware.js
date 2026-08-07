'use strict';

/** Central JSON error handler. */
function errorHandler(err, req, res, next) { // eslint-disable-line no-unused-vars
  const status = err.status || err.statusCode || 500;
  if (status >= 500) {
    console.error('[api]', err);
  }
  res.status(status).json({
    code: status,
    message: status >= 500 ? 'Internal server error' : err.message,
  });
}

module.exports = { errorHandler };