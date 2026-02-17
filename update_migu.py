import requests
import re

def main():
    # --- 1. 初始化列表 ---
    final_output = []
    
    # --- 2. 精品频道 (你测试过有效的源) ---
    final_output.append("精品频道,#genre#")
    try:
        r = requests.get("http://adultiptv.net/chs.m3u", timeout=10)
        if r.status_code == 200:
            name = ""
            for line in r.text.split('\n'):
                if "#EXTINF" in line: name = line.split(',')[-1].strip()
                elif "http" in line and name:
                    final_output.append(f"{name},{line.strip()}")
                    name = ""
    except: pass

    # --- 3. 国内高清 (放弃 fanmingming，改用更硬核的实时维护站) ---
    # 这些源直接爬取目前活跃的单播/组播列表，更新频率以小时计
    sources = [
        "https://raw.githubusercontent.com/ssili126/tv/main/itvlist.txt", # 运营商直出源
        "https://raw.githubusercontent.com/yrosxml/IPTV/main/IPTV.m3u"   # 每日筛选源
    ]
    
    final_output.append("国内高清,#genre#")
    for url in sources:
        try:
            res = requests.get(url, timeout=10)
            if res.status_code == 200:
                # 兼容 txt 和 m3u 两种格式的解析逻辑
                content = res.text
                if "#EXTINF" in content: # 处理 m3u
                    temp_name = ""
                    for line in content.split('\n'):
                        line = line.strip()
                        if "#EXTINF" in line: temp_name = line.split(',')[-1].strip()
                        elif "http" in line and temp_name:
                            if any(x in temp_name for x in ["CCTV", "卫视"]):
                                final_output.append(f"{temp_name},{line}")
                            temp_name = ""
                else: # 处理 txt (频道名,链接)
                    for line in content.split('\n'):
                        if "," in line and "http" in line:
                            final_output.append(line.strip())
            if len(final_output) > 20: break # 抓够了就收工
        except: continue

    # --- 4. 写入你的 migu.txt ---
    with open("migu.txt", "w", encoding="utf-8") as f:
        f.write("\n".join(final_output))
    print("全手工硬核源更新完成")

if __name__ == "__main__":
    main()
