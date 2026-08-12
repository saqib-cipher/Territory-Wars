FROM node:18-alpine

WORKDIR /app

COPY server/package*.json ./server/
RUN cd server && npm install --only=production

COPY server/ ./server/

EXPOSE 8080

ENV PORT=8080
ENV NODE_ENV=production

CMD ["node", "server/src/index.js"]
