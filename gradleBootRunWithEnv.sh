#!/bin/bash
REGEX_ENV_VARIABLE="^[A-Z]+[^=]"
REGEX_ENV_VALUE="(?<==).*$"
env_file=".env"

for env_var in $(cat $env_file); do
    env_variable=$(echo $env_var | grep -Eo $REGEX_ENV_VARIABLE)
    env_value=$(echo $env_var | grep -Po $REGEX_ENV_VALUE)
    echo "Setting Environment Variable: $env_variable"
    export "$env_variable=$env_value"
     
done

echo "Running Gradle Boot Run:"
./gradlew bootRun

wait
echo "Gradle Boot Run Finished."
