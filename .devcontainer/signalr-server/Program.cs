using System;
using System.Threading.Tasks;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.SignalR;
using Microsoft.Extensions.DependencyInjection;

var builder = WebApplication.CreateBuilder(args);
builder.Services.AddSignalR();

var app = builder.Build();
app.MapHub<TestHub>("/hub");

var hubContext = app.Services.GetRequiredService<IHubContext<TestHub>>();
var random = new Random();
_ = Task.Run(async () =>
{
    while (true)
    {
        await Task.Delay(TimeSpan.FromSeconds(random.Next(1, 5)));
        await hubContext.Clients.All.SendAsync("EventA","EventA"+ Guid.NewGuid().ToString());
        await Task.Delay(TimeSpan.FromSeconds(random.Next(1, 2)));
        await hubContext.Clients.All.SendAsync("EventB","EventB"+ Guid.NewGuid().ToString());
    }
});

app.Run("http://0.0.0.0:8080");

class TestHub : Hub { }

