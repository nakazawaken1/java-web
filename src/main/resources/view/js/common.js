jQuery('nav').after('<div id="nav"></div>');
jQuery('#nav').height(jQuery('nav').height());
jQuery('#command').after('<div id="command-spacer"></div>');
jQuery('#command-spacer').height(jQuery('#command').height());
jQuery(function($) {
    $('[data-load]').each(function() {
        var $this = $(this);
        $this.load($this.attr('data-load'));
    });
    $('.focus:first').focus();
});
