#!/bin/sh
set -e

# Plataformas como o Render escolhem a porta em tempo de execução e a
# publicam na variável PORT; localmente e no docker-compose ela não existe,
# e o Nginx cai no padrão de sempre (8080). Um "sed" bem específico em vez do
# mecanismo de templates do próprio Nginx: aquele substitui toda variável de
# ambiente que aparecer no arquivo, o que corromperia variáveis do próprio
# Nginx como $uri usadas no nginx.conf.
if [ -n "$PORT" ] && [ "$PORT" != "8080" ]; then
    sed -i "s/listen 8080;/listen $PORT;/" /etc/nginx/conf.d/default.conf
fi
