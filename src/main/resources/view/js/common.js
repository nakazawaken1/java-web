jQuery(function($) {
    $('[data-load]').each(function() {
        var $this = $(this);
        $this.load($this.attr('data-load'));
    });
});
