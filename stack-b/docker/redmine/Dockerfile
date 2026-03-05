FROM redmine:5.1

ENV RAILS_RELATIVE_URL_ROOT=/app4

# Mount Rails app at /app4 using Rack URLMap
# This tells both Rack and Rails that the app lives at /app4:
# - Incoming /app4/... requests get stripped to /... before Rails routing
# - Rails generates asset/route URLs with /app4 prefix
RUN printf "require_relative 'config/environment'\n\nmap '/app4' do\n  run Rails.application\nend\n" \
    > /usr/src/redmine/config.ru
