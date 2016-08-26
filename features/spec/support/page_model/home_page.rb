class HomePage < SitePrism::Page
  set_url "/"

  element :primary, ".primary"

  def press_primary_button
    primary.click
    wait_for_submit
  end

  def wait_for_submit
    sleep 0.5
  end
end
