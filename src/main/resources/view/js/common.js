jQuery(function($) {
    $('nav').after('<div id="nav"></div>');
    $('#nav').height($('nav').height());
    $('#command').after('<div id="command-spacer"></div>');
    $('#command-spacer').height($('#command').height());
    $('[data-load]').each(function() {
        var $this = $(this);
        $this.load($this.attr('data-load'));
    });
    $('.focus:first').focus();
});
