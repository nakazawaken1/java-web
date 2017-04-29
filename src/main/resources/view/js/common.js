jQuery(function($) {
    $('#nav', this).height($('nav').height());
    $('[data-load]').each(function() {
        var $this = $(this);
        $this.load($this.attr('data-load'));
    });
});
