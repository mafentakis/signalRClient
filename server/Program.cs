using Microsoft.AspNetCore.SignalR;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddSignalR();
builder.Services.AddHostedService<EventEmitterService>();

var app = builder.Build();

app.MapHub<EventHub>("/hub/events");

app.Run();

public class EventHub : Hub
{
}

public class EventEmitterService : BackgroundService
{
    private static readonly string[] Events = new[] { "EventA", "EventB" };
    private readonly IHubContext<EventHub> _hubContext;
    private readonly ILogger<EventEmitterService> _logger;

    public EventEmitterService(IHubContext<EventHub> hubContext, ILogger<EventEmitterService> logger)
    {
        _hubContext = hubContext;
        _logger = logger;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        var index = 0;
        while (!stoppingToken.IsCancellationRequested)
        {
            var eventName = Events[index % Events.Length];
            var payload = new EventPayload(eventName, DateTimeOffset.UtcNow);
            _logger.LogInformation("Publishing {EventName} with payload {@Payload}", eventName, payload);

            try
            {
                await _hubContext.Clients.All.SendAsync(eventName, payload, cancellationToken: stoppingToken);
            }
            catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
            {
                break;
            }

            index++;
            try
            {
                await Task.Delay(TimeSpan.FromSeconds(1), stoppingToken);
            }
            catch (OperationCanceledException)
            {
                break;
            }
        }
    }

    private record EventPayload(string Name, DateTimeOffset Timestamp);
}
