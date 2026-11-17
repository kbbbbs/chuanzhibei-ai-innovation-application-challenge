FROM node:24.11.0-alpine3.22@sha256:f36fed0b2129a8492535e2853c64fbdbd2d29dc1219ee3217023ca48aebd3787 AS build
WORKDIR /build
COPY ai-learning-frontend/package.json ai-learning-frontend/package-lock.json ./
RUN npm ci
COPY ai-learning-frontend/ ./
ENV VITE_API_BASE_URL=/backend
RUN npm run build

FROM nginx:1.28.0-alpine@sha256:30f1c0d78e0ad60901648be663a710bdadf19e4c10ac6782c235200619158284
COPY deploy/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
