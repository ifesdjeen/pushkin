class @Eventoverse.Graphs.Canvas
  defaults:
    x:
      caption: ""
    y:
      caption: ""
    interpolate: "cardinal"
    margin:
      top: 20
      right: 20
      bottom: 30
      left: 50

  constructor: (@selector, @attrs)->
    @elements = []

    @width = 1000 - @defaults.margin.left - @defaults.margin.right
    @height = 200 - @defaults.margin.top - @defaults.margin.bottom

    @x = d3.time.scale()
      .range([0, @width])

    @y = d3.scale.linear()
      .range([@height, 0])

    @svg = d3.select(@selector).append("svg")
      .attr("width", @width + @defaults.margin.left + @defaults.margin.right)
      .attr("height", @height + @defaults.margin.top + @defaults.margin.bottom)
      .append("g")
      .attr("transform", "translate(" + @defaults.margin.left + "," + @defaults.margin.top + ")")

    @svg.append("defs").append("clipPath")
      .attr("id", "clip")
      .append("rect")
      .attr("width", @width)
      .attr("height", @height)

    @prepareAxes()
    this

  addElement: (element, args)->
    @elements.push(new element(this, args))
    this
  xAxis: ()->
    d3.svg.axis()
      .scale(@x)
      .orient("bottom")
      .tickSubdivide(false)
      .tickSize(5, 0, 5)

  yAxis: ()->
    d3.svg.axis()
      .scale(@y)
      .orient("left")
      .ticks(10)
        .tickSubdivide(1)
      .tickSize(10, 5, 10)

  prepareAxes: ()->
    @axes = @svg.append("svg:g")
      .classed("rules", true)

    @axes.append("g")
      .classed("x axis", true)
      .attr("transform", "translate(0," + @height + ")")
      .append("text")
      .attr("x", @width)
      .attr("dy", "1.5em")
      .style("text-anchor", "end")
      .text(@attrs.x.caption || @defaults.x.caption)

    @axes.append("g")
      .classed("x grid", true)
      .attr("transform", "translate(0," + @height + ")")


    @axes.append("g")
      .classed("y grid", true)


    renderedYAxis = @axes.append("g")
      .classed("y axis", true)

    renderedYAxis.append("text")
      .attr("transform", "rotate(-90)")
      .attr("y", 6)
      .attr("dy", ".71em")
      .style("text-anchor", "end")
      .text(@attrs.y.caption || @defaults.y.caption)

    this

  render: (data, opts)->
    @renderAxes(data)

    _.each @elements, (element)->
      if (opts && opts.skip == element)
        return

      console.log("Starting rendering: ", element, data)
      element.render(data)
      console.log("Finished rendering: ", element, data)

  xFormatter: (d)-> new Date(d.x)

  renderAxes: (all_data)->
    data = Eventoverse.Utils.mergeData(all_data)

    @x.domain(d3.extent(data, @xFormatter))

    very_min = d3.min(data, (d)-> d.y)
    very_max = d3.max(data, (d)-> d.y)

    @y.domain([very_min - 1, very_max + 1])

    @svg.select(".x.axis").call(@xAxis())
    @svg.select(".y.axis").call(@yAxis())

    @svg.select(".x.grid").call(@xAxis().tickSize(-@height,0,0).tickFormat(""))
    @svg.select(".y.grid").call(@yAxis().tickSize(-@width,0,0).tickFormat(""))

    this

class @Eventoverse.Graphs.RealTimeCanvas extends Eventoverse.Graphs.Canvas
  xFormatter: ()-> ""
  constructor: (@selector, @attrs)->
    super(@selector, @attrs)

    @x = d3.scale.linear()
      .domain([1, @attrs.buffer_size - 2])
      .range([0, @width])
    @

    @y = d3.scale.linear()
      .domain([0, 3])
      .range([@height, 0])

    @attrs.paused = false

  render: (data, opts)->
    @data = data
    @renderAxes(data)

    _.each @elements, (element)->
      if (opts && opts.skip == element)
        return

      console.log("Starting rendering: ", element, data)
      element.render(data)
      console.log("Finished rendering: ", element, data)

  renderAxes: (all_data)->
    data = Eventoverse.Utils.mergeData(all_data)

    @svg.select(".x.axis").call(@xAxis())
    @svg.select(".y.axis").call(@yAxis())

    @svg.select(".x.grid").call(@xAxis().tickSize(-@height,0,0).tickFormat(""))
    @svg.select(".y.grid").call(@yAxis().tickSize(-@width,0,0).tickFormat(""))

    this


class @Eventoverse.Graphs.Controls
  constructor: (@canvas, @args)->
  render: ()->
    @element = $("<div class='btn-group'><button class='btn pause'><i class='icon-pause'></i></button><button class='btn play'><i class='icon-play'></i></button></div>")

    $(@canvas.selector).append(@element)

    @pause_button = $(@element).find('.btn.pause')
    @resume_button = $(@element).find('.btn.play')

    @pause_button.click @pause
    @resume_button.click @resume
    @resume_button.hide()
  pause: ()=>
    @tipsy(@canvas.data)

    @canvas.attrs.paused = true
    @pause_button.hide()
    @resume_button.show()
  resume: ()=>
    @removeTipsy()
    @canvas.attrs.paused = false
    @resume_button.hide()
    @pause_button.show()

  removeTipsy: ()->
    @circles.remove()

  tipsy: (data)=>
    @circles = @canvas.svg.append('svg:g').selectAll('.data-point').data(data.values)

    @circles.enter()
      .append('svg:circle')
      .attr('class', 'data-point')
      .attr('cx', (d, i)=> @canvas.x(i))
      .attr('cy', (d, i)=> @canvas.y(d.y))
      .attr('r', ()-> 2)

    args = @args
    tipsy =
      gravity: 'w'
      html: true
      title: ()->
        args.tip_formatter(this.__data__)

    $('svg circle').tipsy(tipsy)
