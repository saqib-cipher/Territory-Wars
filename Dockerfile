FROM node:18-alpine

WORKDIR /app/server

COPY server/package*.json ./
RUN npm install --only=production

COPY server/ ./

EXPOSE 8080

ENV PORT=8080
ENV NODE_ENV=production

CMD ["node", "src/index.js"]
