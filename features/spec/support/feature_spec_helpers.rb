module FeatureSpecHelpers
  def goto_page(klass, args = {})
    page = klass.new
    page.load args
    yield page if block_given?
  end

  def expect_page(klass)
    page = klass.new
    expect(page).to be_displayed
    yield page if block_given?
  end

  def sign_in
    goto_page HomePage do
      expect_page GuissoLogin do |page|
        page.form.user_name.set "admin@instedd.org"
        page.form.password.set "admin123"
        page.form.login.click
      end
    end
  end
end