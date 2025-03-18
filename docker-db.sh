# makes a docker db you can use for local testing, installing postgres and setting up the username and password
# also deletes any existing docker db with the same name




# Check if a container named 'attractions-postgres' exists
container_id=$(docker ps -a -q -f name=db-do-postgres)

# If the container exists, delete it
if [ -n "$container_id" ]; then
  echo "Container 'db-do-postgres' exists. Deleting it..."
  docker rm -f db-do-postgres
else
  echo "Creating new container 'db-do-postgres'..."
fi


docker run -p5432:5432 --name db-do-postgres -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=password -e POSTGRES_DB=db_do -d postgres


