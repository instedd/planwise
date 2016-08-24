describe Home do
  it "should login" do
    goto_page Home do
      expect_page GuissoLogin do |page|
      	page.form.user_name.set "admin@instedd.org"
      	page.form.password.set "admin123"
      	page.form.login.click
      end
    end

    expect(page).to have_content 'Projects'
  end
end
