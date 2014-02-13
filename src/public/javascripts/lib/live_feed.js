var channel, pusher;
window.onload = function() {

  pusher = new Pusher('abc123');
  channel = pusher.subscribe('my-channel');
  return channel.bind('test-event', function(data) {
    return console.log(data);
  });
};
