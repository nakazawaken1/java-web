/*
tablescroll.js 1.0.0 2017-06-09
Copyright(C) 2017 nakazawaken1@gmail.com
License at http://www.apache.org/licenses/LICENSE-2.0
required jQuery 1.8 over
*/
(function($) {
$.extend({
    scrollbarWidth: function() {
        var width;
        if(width == undefined) {
            var outer = $('<div style="height:50px;overflow:auto"></div>').appendTo('body');
            var inner = $('<div></div>').appendTo(outer);
            width = inner.innerWidth() - inner.height(60).innerWidth();
            outer.remove();
        }
        return width;
    }
});
$.fn.extend({
    widths: function(widths) {
        var i = 0;
        return this.each(function() {
            var w = $(this).innerWidth() - Math.floor($(this).width());
            var width = -w;
            for(var j = 0, j2 = $(this).attr('colspan') || 1; j < j2; j++) {
                width += widths.get(i) + w;
                i++;
            }
            $(this).width(width);
        });
    },
    sum: function() {
        var n = 0;
        this.each(function() {
            n += Number(this);
        });
        return n;
    },
    tableScroll: function(height, space) {
        space = Number(space) || 0;
        return this.each(function() {
            var $this = $(this);
            var heads = $this.find('thead tr:last th,thead tr:last td');
            var bodys = $this.find('tbody tr:first th, tbody tr:first td');
            var foots = $this.find('tfoot tr:first th,tfoot tr:first td');
            if($this.parent().attr('data-id') == 'inner') {
                $this.unwrap().unwrap();
                heads.width('');
                bodys.width('');
                foots.width('');
                $this.find('thead').css({position: '', top: '', display: ''});
                $this.find('tbody').css({display: ''});
                $this.find('tfoot').css({position: '', bottom: '', display: ''});
            }
            var fixedHeight = $this.outerHeight() - $this.find('tbody').outerHeight();
            var headHeight = fixedHeight - $this.find('tfoot').outerHeight();
            var footHeight = fixedHeight - $this.find('thead').outerHeight();
            $this.wrap('<div style="position:relative;padding-top:' + headHeight + 'px;padding-bottom:' + footHeight + 'px"></div>')
                .wrap('<div data-id="inner" style="overflow:auto;height:' + ((height || $this.attr('data-scroll')) - fixedHeight) + 'px"></div>');
            var widths = bodys.map(function() {
                return $(this).outerWidth() + space;
            });
            var width = $this.outerWidth();
            var over = widths.sum() - width;
            if(over > 0) {
                var minus = Math.floor(over / widths.length);
                var rest = over % widths.length;
                widths = widths.map(function() {
                    var n = minus;
                    if(rest > 0) {
                        n++;
                        rest--;
                    }
                    return this - n;
                });
            }
            $this.find('thead').css({position: 'absolute', top: 0, display: 'block'});
            $this.find('tbody').css({display: 'block'});
            $this.find('tfoot').css({position: 'absolute', bottom: 0, display: 'block'});
            heads.widths(widths, space);
            bodys.widths(widths, space);
            foots.widths(widths, space);
            $this.parent().parent().outerWidth($this.find('thead').outerWidth() + $.scrollbarWidth());
        });
    }
});
$(function() {
    var resize = function() {
        $('table[data-scroll]').tableScroll();
    };
    resize();
    var timer;
    $(window).on('resize', function() {
        if (timer != null) {
            clearTimeout(timer);
        }
        timer = setTimeout(resize, 100);
    });
});
})(jQuery);
