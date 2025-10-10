using Microsoft.AspNetCore.SignalR;
using SignalRServer.Hubs;

public class EventEmitterService : BackgroundService
{
    private readonly IHubContext<EventHub> _hubContext;
    private readonly ILogger<EventEmitterService> _logger;
    private int _eventCounter = 0;

    public EventEmitterService(IHubContext<EventHub> hubContext, ILogger<EventEmitterService> logger)
    {
        _hubContext = hubContext;
        _logger = logger;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        _logger.LogInformation("Event Emitter Service started");

        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                // Alternate between EventA and EventB
                if (_eventCounter % 2 == 0)
                {
                    var message = $"EventA message #{_eventCounter / 2 + 1} at {DateTime.UtcNow:HH:mm:ss}";
                    await _hubContext.Clients.All.SendAsync("EventA", message, stoppingToken);
                    _logger.LogInformation($"Emitted EventA: {message}");
                }
                else
                {
                    var message = $"EventB message #{(_eventCounter + 1) / 2} at {DateTime.UtcNow:HH:mm:ss}";
                    await _hubContext.Clients.All.SendAsync("EventB", message, stoppingToken);
                    _logger.LogInformation($"Emitted EventB: {message}");
                }

                _eventCounter++;
                await Task.Delay(1000, stoppingToken);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error emitting event");
            }
        }

        _logger.LogInformation("Event Emitter Service stopped");
    }
}

