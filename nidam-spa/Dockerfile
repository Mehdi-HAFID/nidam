FROM node:24-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:stable-alpine
# Build output goes into .../react-ui
COPY --from=build /app/build /usr/share/nginx/html/react-ui
COPY nginx.conf /etc/nginx/conf.d/default.conf
