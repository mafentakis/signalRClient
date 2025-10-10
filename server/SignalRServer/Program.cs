using SignalRServer.Hubs;

var builder = WebApplication.CreateBuilder(args);

// Add services to the container
builder.Services.AddSignalR();
builder.Services.AddCors(options =>
{
    options.AddPolicy("AllowAll", policy =>
    {
        policy.AllowAnyOrigin()
              .AllowAnyMethod()
              .AllowAnyHeader();
    });
});

// Add hosted service for event emission
builder.Services.AddHostedService<EventEmitterService>();

var app = builder.Build();

// Configure the HTTP request pipeline
app.UseCors("AllowAll");
app.MapHub<EventHub>("/eventhub");

app.MapGet("/", () => "SignalR Server is running. Connect to /eventhub");

app.Run();

