using Microsoft.AspNetCore.SignalR;

namespace SignalRServer.Hubs;

public class EventHub : Hub
{
    public override async Task OnConnectedAsync()
    {
        Console.WriteLine($"Client connected: {Context.ConnectionId}");
        await base.OnConnectedAsync();
    }

    public override async Task OnDisconnectedAsync(Exception? exception)
    {
        Console.WriteLine($"Client disconnected: {Context.ConnectionId}");
        if (exception != null)
        {
            Console.WriteLine($"Disconnection error: {exception.Message}");
        }
        await base.OnDisconnectedAsync(exception);
    }
}

