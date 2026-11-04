# syntax=docker/dockerfile:1

############################
# deps — cached unless package.json changes
############################
FROM node:22-alpine AS deps
WORKDIR /app
COPY package*.json ./
RUN npm install

############################
# dev — Vite dev server, source bind-mounted by compose
############################
FROM deps AS dev
WORKDIR /app
EXPOSE 5173
CMD ["npm", "run", "dev"]

############################
# build — static bundle
############################
FROM deps AS build
WORKDIR /app
COPY . .
RUN npm run build

############################
# runtime — nginx serving the bundle, proxying /api to the api container
############################
FROM nginx:1.27-alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s --retries=5 CMD wget -qO- http://localhost/ || exit 1
