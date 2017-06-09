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
    widths: function(widths, space) {
        var i = 0;
        return this.each(function() {
            var width = space;
            for(var j = 0, j2 = $(this).attr('colspan') || 1; j < j2; j++) {
                width += widths.get(i) - space;
                i++;
            }
            $(this).outerWidth(width);
        });
    },
    tableScroll: function(height, space) {
        space = Number(space) || 2;
        return this.each(function() {
            var $this = $(this);
            var fixedHeight = $this.outerHeight() - $this.find('tbody').outerHeight();
            var headHeight = fixedHeight - $this.find('tfoot').outerHeight();
            var footHeight = fixedHeight - $this.find('thead').outerHeight();
            var heads = $this.find('thead tr:last th,thead tr:last td');
            var bodys = $this.find('tbody tr:first th, tbody tr:first td');
            var foots = $this.find('tfoot tr:first th,tfoot tr:first td');
            var widths = bodys.map(function() {
                return $(this).outerWidth() + space;
            });
            $this.wrap('<div style="position:relative;padding-top:' + headHeight + 'px;padding-bottom:' + footHeight + 'px"></div>')
                .wrap('<div style="overflow:auto;height:' + ((height || $this.attr('data-scroll')) - fixedHeight) + 'px"></div>');
            $this.find('thead').css({position: 'absolute', top: 0});
            $this.find('tfoot').css({position: 'absolute', bottom: 0});
            heads.widths(widths, space);
            bodys.widths(widths, space);
            foots.widths(widths, space);
            $this.parent().parent().outerWidth($this.find('tbody').outerWidth() + $.scrollbarWidth() + 1);
        });
    }
});
$(function() {
    $('table[data-scroll]').tableScroll();
});
})(jQuery);
