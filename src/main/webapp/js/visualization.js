function drawD3CategoryTreeGraph(json) {
				var m = [20, 120, 20, 120],
				    w = 1280 - m[1] - m[3],
				    h = 800 - m[0] - m[2],
				    i = 0,
				    root;
				
				var tree = d3.layout.tree()
				    .size([h, w]);
				
				var diagonal = d3.svg.diagonal()
				    .projection(function(d) { return [d.y, d.x]; });
				
				var vis = d3.select("#categoryWindowBody").append("svg:svg")
				    .attr("width", w + m[1] + m[3])
				    .attr("height", h + m[0] + m[2])
				  .append("svg:g")
				    .attr("transform", "translate(" + m[3] + "," + m[0] + ")");
				    
				root = json;
				  root.x0 = h / 2;
				  root.y0 = 0;
				
			    function toggleAll(d) {
			      	if (d.children) {
			        	d.children.forEach(toggleAll);
			      		toggle(d);
			    	}
			  	}
			  	root.children.forEach(toggleAll);
			  	update(root);
			  	
			  	function update(source) {
				  var duration = d3.event && d3.event.altKey ? 5000 : 500;
				
				  // Compute the new tree layout.
				  var nodes = tree.nodes(root).reverse();
				
				  // Normalize for fixed-depth.
				  nodes.forEach(function(d) { d.y = d.depth * 180; });
				
				  // Update the nodes…
				  var node = vis.selectAll("g.node")
				      .data(nodes, function(d) { return d.id || (d.id = ++i); });
				
				  // Enter any new nodes at the parent's previous position.
				  var nodeEnter = node.enter().append("svg:g")
				      .attr("class", "node")
				      .attr("transform", function(d) { return "translate(" + source.y0 + "," + source.x0 + ")"; })
				      .on("click", function(d) { toggle(d); update(d); });
				
				  nodeEnter.append("svg:circle")
				      .attr("r", 1e-6)
				      .style("fill", function(d) { return d._children ? "lightsteelblue" : "#fff"; });
				
				  nodeEnter.append("svg:text")
				      .attr("x", function(d) { return d.children || d._children ? -10 : 10; })
				      .attr("dy", ".35em")
				      .attr("text-anchor", function(d) { return d.children || d._children ? "end" : "start"; })
				      .text(function(d) { return d.label; })
				      .style("fill-opacity", 1e-6);
				
				  // Transition nodes to their new position.
				  var nodeUpdate = node.transition()
				      .duration(duration)
				      .attr("transform", function(d) { return "translate(" + d.y + "," + d.x + ")"; });
				
				  nodeUpdate.select("circle")
				      .attr("r", 4.5)
				      .style("fill", function(d) { return d._children ? "lightsteelblue" : "#fff"; });
				
				  nodeUpdate.select("text")
				      .style("fill-opacity", 1);
				
				  // Transition exiting nodes to the parent's new position.
				  var nodeExit = node.exit().transition()
				      .duration(duration)
				      .attr("transform", function(d) { return "translate(" + source.y + "," + source.x + ")"; })
				      .remove();
				
				  nodeExit.select("circle")
				      .attr("r", 1e-6);
				
				  nodeExit.select("text")
				      .style("fill-opacity", 1e-6);
				
				  // Update the links…
				  var link = vis.selectAll("path.link")
				      .data(tree.links(nodes), function(d) { return d.target.id; });
				
				  // Enter any new links at the parent's previous position.
				  link.enter().insert("svg:path", "g")
				      .attr("class", "link")
				      .attr("d", function(d) {
				        var o = {x: source.x0, y: source.y0};
				        return diagonal({source: o, target: o});
				      })
				    .transition()
				      .duration(duration)
				      .attr("d", diagonal);
				
				  // Transition links to their new position.
				  link.transition()
				      .duration(duration)
				      .attr("d", diagonal);
				
				  // Transition exiting nodes to the parent's new position.
				  link.exit().transition()
				      .duration(duration)
				      .attr("d", function(d) {
				        var o = {x: source.x, y: source.y};
				        return diagonal({source: o, target: o});
				      })
				      .remove();
				
				  // Stash the old positions for transition.
				  nodes.forEach(function(d) {
				    d.x0 = d.x;
				    d.y0 = d.y;
				  });
				}
				
				// Toggle children.
				function toggle(d) {
				  if (d.children) {
				    d._children = d.children;
				    d.children = null;
				  } else {
				    d.children = d._children;
				    d._children = null;
				  }
				}
			  	
			}
			
			function drawD3CategoryCircularGraph(json) {
				var w = 1280,
				    h = 800,
				    rx = w / 2,
				    ry = h / 2,
				    m0,
				    rotate = 0;
				
				var cluster = d3.layout.cluster()
				    .size([360, ry - 120])
				    .sort(null);
				
				var diagonal = d3.svg.diagonal.radial()
				    .projection(function(d) { return [d.y, d.x / 180 * Math.PI]; });
				
				var svg = d3.select("#categoryWindowBody").append("div")
					.attr("class", 'svgHolderDiv')
				    .style("width", w + "px")
				    .style("height", w + "px");
				    
				var vis = svg.append("svg:svg")
				    .attr("width", w)
				    .attr("height", w)
				  .append("svg:g")
				    .attr("transform", "translate(" + rx + "," + ry + ")");
				
				vis.append("svg:path")
				    .attr("class", "arc")
				    .attr("d", d3.svg.arc().innerRadius(ry - 120).outerRadius(ry).startAngle(0).endAngle(2 * Math.PI))
				    .on("mousedown", mousedown);
				    
				var nodes = cluster.nodes(json);

				var link = vis.selectAll("path.link")
				    .data(cluster.links(nodes))
				  .enter().append("svg:path")
				    .attr("class", "link")
				    .attr("d", diagonal);
				
				var node = vis.selectAll("g.node")
				    .data(nodes)
				  .enter().append("svg:g")
				    .attr("class", "node")
				    .attr("transform", function(d) { return "rotate(" + (d.x - 90) + ")translate(" + d.y + ")"; })
				
				node.append("svg:circle")
				    .attr("r", 3);
				
				node.append("svg:text")
				    .attr("dx", function(d) { return d.x < 180 ? 8 : -8; })
				    .attr("dy", ".31em")
				    .attr("text-anchor", function(d) { return d.x < 180 ? "start" : "end"; })
				    .attr("transform", function(d) { return d.x < 180 ? null : "rotate(180)"; })
				    .text(function(d) { return d.label; });
				    
				d3.select(window)
				    .on("mousemove", mousemove)
				    .on("mouseup", mouseup);
				
				function mouse(e) {
				  return [e.pageX - rx, e.pageY - ry];
				}
				
				function mousedown() {
				  m0 = mouse(d3.event);
				  d3.event.preventDefault();
				}
				
				function mousemove() {
				  if (m0) {
				    var m1 = mouse(d3.event),
				        dm = Math.atan2(cross(m0, m1), dot(m0, m1)) * 180 / Math.PI,
				        tx = "translate3d(0," + (ry - rx) + "px,0)rotate3d(0,0,0," + dm + "deg)translate3d(0," + (rx - ry) + "px,0)";
				    svg
				        .style("-moz-transform", tx)
				        .style("-ms-transform", tx)
				        .style("-webkit-transform", tx);
				  }
				}
				
				function mouseup() {
				  if (m0) {
				    var m1 = mouse(d3.event),
				        dm = Math.atan2(cross(m0, m1), dot(m0, m1)) * 180 / Math.PI,
				        tx = "rotate3d(0,0,0,0deg)";
				
				    rotate += dm;
				    if (rotate > 360) rotate -= 360;
				    else if (rotate < 0) rotate += 360;
				    m0 = null;
				
				    svg
				        .style("-moz-transform", tx)
				        .style("-ms-transform", tx)
				        .style("-webkit-transform", tx);
				
				    vis
				        .attr("transform", "translate(" + rx + "," + ry + ")rotate(" + rotate + ")")
				      .selectAll("g.node text")
				        .attr("dx", function(d) { return (d.x + rotate) % 360 < 180 ? 8 : -8; })
				        .attr("text-anchor", function(d) { return (d.x + rotate) % 360 < 180 ? "start" : "end"; })
				        .attr("transform", function(d) { return (d.x + rotate) % 360 < 180 ? null : "rotate(180)"; });
				  }
				}
				
				function cross(a, b) {
				  return a[0] * b[1] - a[1] * b[0];
				}
				
				function dot(a, b) {
				  return a[0] * b[0] + a[1] * b[1];
				}
			}
			
			function drawD3EntityGraph(json) {
				var width = 1000,
			    	height = 700,
			    	r = 6;
			
				var svg = d3.select("div#graphWindowBody").append("svg:svg")
				    .attr("width", width)
				    .attr("height", height);
				
				var force = self.force = d3.layout.force()
				    .nodes(json.nodes)
			        .links(json.links)
				    .linkDistance(150)
				    .charge(-600)
			        .size([width, height])
			        .start();
				
				var node_drag = d3.behavior.drag()
			        .on("dragstart", dragstart)
			        .on("drag", dragmove)
			        .on("dragend", dragend);
			
			    function dragstart(d, i) {
			        force.stop() // stops the force auto positioning before you start dragging
			    }
			
			    function dragmove(d, i) {
			        d.px += d3.event.dx;
			        d.py += d3.event.dy;
			        d.x += d3.event.dx;
			        d.y += d3.event.dy; 
			        tick(); // this is the key to make it work together with updating both px,py,x,y on d !
			    }
			
			    function dragend(d, i) {
			        d.fixed = true; // of course set the node to fixed so the force doesn't include the node in its auto positioning stuff
			        tick();
			        force.resume();
			    }
				
				var link = svg.selectAll(".link")
				    .data(json.links)
				  .enter().append("line")
				    .attr("class", "link")
				    .attr("x1", function(d) { return d.source.x; })
			        .attr("y1", function(d) { return d.source.y; })
			        .attr("x2", function(d) { return d.target.x; })
			        .attr("y2", function(d) { return d.target.y; });
				
				var node = svg.selectAll(".node")
				    .data(json.nodes)
				  .enter().append("g")
				    .attr("class", function(d) {
				    	return "node " + d.group;
				    })
				    .call(node_drag);
				    
				
				node.append("svg:circle")
				    .attr("r", function(d) { 
				    	if (d.group == "PAGE_TITLE")
				    		return r +5;
				    	else
				    		return r - 0.75;
				    })
				    // .style("fill", function(d) { return fill(d.group); 
				    // })
				    // .style("stroke", function(d) { return d3.rgb(fill(d.group)).darker(); })
				    //.call(node_drag);
				
				node.append("text")
				    .attr("dx", 12)
				    .attr("dy", ".35em")
				    .text(function(d) { return d.name });
				
				force.on("tick", tick);
				
				function tick() {
				  // node.attr("cx", function(d) { return d.x = Math.max(r, Math.min(width - r, d.x)); })
        				// .attr("cy", function(d) { return d.y = Math.max(r, Math.min(height - r, d.y)); });
				
				  // node.attr("transform", function(d) { return "translate(" + Math.max(r, Math.min(width - r, d.x)) 
				  	// + "," + Math.max(r, Math.min(height - r, d.y))+ ")"; });
				  	
				  	 node.attr("transform", function(d) { return "translate(" + d.x + "," + d.y + ")"; });
				  	link.attr("x1", function(d) { return d.source.x; })
				      .attr("y1", function(d) { return d.source.y; })
				      .attr("x2", function(d) { return d.target.x; })
				      .attr("y2", function(d) { return d.target.y; });
				};
			}