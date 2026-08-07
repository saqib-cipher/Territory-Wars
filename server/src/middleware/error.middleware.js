'use strict';

/** Central error handler. Never leaks stack traces in production. */
function errorHandler(err, req, res, next) { // eslint-disable-line no-unused-vars
  const status = err.status || 500;
  const message = status >= 500 ? 'Internal server error' : err.message;

  if (status >= 500) {
    console.error('[error]', err);
  }

  res.status(status).json({
    code: status,
    message,
  });
}

module.exports = { errorHandler };