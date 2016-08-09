describe String do
  it "should open google" do
    visit '/'
    click_link 'Gmail'
    screenshot_and_save_page
  end
end
