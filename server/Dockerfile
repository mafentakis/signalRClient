# Use the official .NET 8 SDK image for building
FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src

# Copy the project file and restore dependencies
COPY SignalRServer/SignalRServer.csproj SignalRServer/
RUN dotnet restore SignalRServer/SignalRServer.csproj

# Copy the rest of the source code
COPY SignalRServer/ SignalRServer/

# Build the application
WORKDIR /src/SignalRServer
RUN dotnet build SignalRServer.csproj -c Release -o /app/build

# Publish the application
FROM build AS publish
RUN dotnet publish SignalRServer.csproj -c Release -o /app/publish

# Use the runtime image for the final stage
FROM mcr.microsoft.com/dotnet/aspnet:8.0 AS final
WORKDIR /app
EXPOSE 5000
COPY --from=publish /app/publish .
ENTRYPOINT ["dotnet", "SignalRServer.dll"]

